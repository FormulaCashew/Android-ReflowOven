#include "esp_event.h"
#include "esp_log.h"
#include "esp_mac.h"
#include "esp_wifi.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "nvs_flash.h"
#include <stdio.h>
#include <string.h>

#include "lwip/err.h"
#include "lwip/netdb.h"
#include "lwip/sockets.h"
#include "lwip/sys.h"

#define EXAMPLE_ESP_WIFI_SSID "esp_test"
#define EXAMPLE_ESP_WIFI_PASS "verySecure"
#define EXAMPLE_ESP_WIFI_CHANNEL 5
#define EXAMPLE_MAX_STA_CONN 2
#define PORT 8080

#if CONFIG_ESP_GTK_REKEYING_ENABLE
#define EXAMPLE_GTK_REKEY_INTERVAL CONFIG_ESP_GTK_REKEY_INTERVAL
#else
#define EXAMPLE_GTK_REKEY_INTERVAL 0
#endif

static const char *TAG = "wifi softAP";

// Define the structure for a Reflow Profile
typedef struct {
  float soak_temp;
  int soak_time;
  float reflow_peak;
  int reflow_time; // Unused in this simple simulation, but good to store
} reflow_profile_t;

// Global State Variables
volatile float current_temp = 25.0; // Current sensor reading
volatile float target_temp = 0.0;   // The immediate temperature goal (e.g., soak temp,
                           // peak temp, room temp)
volatile char oven_status[20] = "IDLE";
volatile int cycle_timer = 0;       // Used for soak countdown
volatile bool is_reflowing = false; // True when a profile is running

// Global variable to hold the active profile parameters
volatile reflow_profile_t active_profile = {0.0, 0, 0.0, 0};

// Define heating/cooling rates
#define HEAT_RATE 2.5
#define COOL_RATE 2.0

// Socket variables
int listen_sock = -1;

void send_status(int sock) {
  char tx_buffer[128];
  // Format the STATUS response based on the defined protocol
  snprintf(tx_buffer, sizeof(tx_buffer), "STATUS;%.1f;%.1f;%s;%d\n",
           current_temp, target_temp, oven_status, cycle_timer);

  int err = send(sock, tx_buffer, strlen(tx_buffer), 0);
  if (err < 0) {
    ESP_LOGE(TAG, "Error occurred during send: errno %d", errno);
  } else {
    ESP_LOGI(TAG, "Sent status: %s", tx_buffer);
  }
}

void handle_command(int sock, const char *rx_buffer) {
    // 1. Create a local, mutable copy for cleaning and processing
    char clean_buffer[128];
    // Copy safely, ensuring null termination
    strncpy(clean_buffer, rx_buffer, sizeof(clean_buffer) - 1);
    clean_buffer[sizeof(clean_buffer) - 1] = '\0';

    // 2. Clean the buffer: Remove trailing newline/carriage return
    // This ensures strcmp and sscanf work reliably against the command string
    char *newline = strchr(clean_buffer, '\n');
    if (newline) {
        *newline = '\0';
    }
    char *cr = strchr(clean_buffer, '\r');
    if (cr) {
        *cr = '\0';
    }

    // --- Command Processing ---

    // A. STATUS? (The most frequent command)
    if (strcmp(clean_buffer, "STATUS?") == 0) {
        ESP_LOGI(TAG, "Command: STATUS? request received.");
        send_status(sock);
        return;
    }

    // B. STOP
    if (strcmp(clean_buffer, "STOP") == 0) {
        // Set the status to COOLING and reset flags/targets
        if (strcmp(oven_status, "IDLE") != 0 && strcmp(oven_status, "COMPLETE") != 0) {
            strcpy(oven_status, "COOLING");
            target_temp = 25.0; // Target room temperature
            is_reflowing = false; // Stop automatic progression
            cycle_timer = 0;
            ESP_LOGW(TAG, "Command: STOP received. Initiating manual COOLING.");
        } else {
            ESP_LOGW(TAG, "Command: STOP ignored, already IDLE/COMPLETE.");
        }
        send_status(sock);
        return;
    }

    // C. START (Parsing the profile)
    if (strstr(clean_buffer, "START;") == clean_buffer) { // Ensure it starts exactly with "START;"
        float soak_temp_in = -1.0, reflow_peak_in = -1.0; 
        int soak_time_in = -1, reflow_time_in = -1; 

        // Protocol: START;SoakTemp(F);SoakTime(I);ReflowPeak(F);ReflowTime(I)
        int matches = sscanf(clean_buffer, "START;%f;%d;%f;%d", 
                             &soak_temp_in, &soak_time_in, 
                             &reflow_peak_in, &reflow_time_in);

        if (matches == 4) { 
            // 1. Save the received profile globally 
            active_profile.soak_temp = soak_temp_in; 
            active_profile.soak_time = soak_time_in; 
            active_profile.reflow_peak = reflow_peak_in; 
            active_profile.reflow_time = reflow_time_in; 

            // 2. Initialize the simulation state 
            target_temp = active_profile.soak_temp; // Start heating toward soak temp
            strcpy(oven_status, "HEATING"); 
            is_reflowing = true; 
            cycle_timer = 0; 

            ESP_LOGI(TAG, "Command: START. Profile set. Target: %.1f deg C, Soak Time: %d s", 
                     target_temp, active_profile.soak_time); 
            send_status(sock); 
        } else { 
            // This error is crucial for debugging the Android side data format
            ESP_LOGE(TAG, "START parsing FAILED! Expected 4 matches, got %d. Command: '%s'", 
                     matches, clean_buffer); 
        } 
        return; 
    }
    
    // D. Unknown Command
    if (strlen(clean_buffer) > 0) {
        ESP_LOGE(TAG, "Unknown command received: '%s'", clean_buffer);
    }
}

void oven_simulator_task(void *pvParameters) {
  while (1) {
    // Only run logic if a process is active (not IDLE, COMPLETE, or ERROR)
    if (strcmp(oven_status, "IDLE") != 0 &&
        strcmp(oven_status, "COMPLETE") != 0 &&
        strcmp(oven_status, "ERROR") != 0) {

      // --- 1. HEATING & RAMPING LOGIC (Temperature is below the immediate
      // goal) ---
      if (current_temp < target_temp) {
        current_temp += HEAT_RATE;

        if (current_temp >= target_temp) {
          current_temp = target_temp; // Clamp the temperature at the goal

          // --- TRANSITIONS upon reaching target ---

          // Transition: HEATING -> SOAKING
          if (strcmp(oven_status, "HEATING") == 0) {
            strcpy(oven_status, "SOAKING");
            // Set timer based on received profile parameter
            cycle_timer = active_profile.soak_time;
            ESP_LOGI(TAG, "Status: SOAKING. Timer: %d", cycle_timer);
          }
          // Transition: REFLOWING -> COOLING (Reached peak temperature)
          else if (strcmp(oven_status, "REFLOWING") == 0) {
            strcpy(oven_status, "COOLING");
            target_temp = 25.0; // Set final target to room temperature
            ESP_LOGI(TAG, "Status: COOLING after peak.");
          }
        }
      }

      // --- 2. HOLDING LOGIC (When temperature equals the target) ---
      else if (strcmp(oven_status, "SOAKING") == 0) {
        // Minor adjustments to maintain temp (P, I, D logic simulation)
        if (current_temp > target_temp)
          current_temp -= 0.05;
        if (current_temp < target_temp)
          current_temp += 0.05;

        cycle_timer--; // Countdown the soak time
        if (cycle_timer <= 0) {
          // Transition: SOAKING -> REFLOWING (Ramp to peak)
          strcpy(oven_status, "REFLOWING");
          // Set the new immediate goal to the received reflow peak
          target_temp = active_profile.reflow_peak;
          ESP_LOGI(TAG, "Status: REFLOWING (Ramp to peak: %.1f)", target_temp);
        }
      }

      // --- 3. COOLING LOGIC (Temperature is above the final room temp target)
      // ---
      else if (current_temp > 25.0 && strcmp(oven_status, "COOLING") == 0) {
        current_temp -= COOL_RATE; // Simulate passive cooling

        if (current_temp <= 25.0) {
          current_temp = 25.0; // Clamp temperature
          strcpy(oven_status, "COMPLETE");
          is_reflowing = false; // Cycle finished
          target_temp = 0.0;
          ESP_LOGI(TAG, "Cooling complete. Status COMPLETE.");
        }
      }
      // Ensures cooling works immediately after a STOP command
      else if (strcmp(oven_status, "COOLING") == 0 && current_temp > 25.0) {
        current_temp -= COOL_RATE;
      }
    }

    vTaskDelay(pdMS_TO_TICKS(1000)); // Update every 1 second
  }
}

void tcp_server_task(void *pvParameters) {
  char rx_buffer[128];
  char addr_str[128];
  int addr_family = AF_INET;
  int ip_protocol = 0;

  // 1. Configure the Socket Address
  struct sockaddr_in dest_addr;
  dest_addr.sin_addr.s_addr = htonl(INADDR_ANY);
  dest_addr.sin_family = AF_INET;
  dest_addr.sin_port = htons(PORT);
  ip_protocol = IPPROTO_IP;

  // 2. Create the Listening Socket
  int listen_sock = socket(addr_family, SOCK_STREAM, ip_protocol);
  if (listen_sock < 0) {
    ESP_LOGE(TAG, "Unable to create socket: errno %d", errno);
    vTaskDelete(NULL);
    return;
  }
  ESP_LOGI(TAG, "Socket created");

  // 3. Bind Socket to the configured address and port
  int err = bind(listen_sock, (struct sockaddr *)&dest_addr, sizeof(dest_addr));
  if (err != 0) {
    ESP_LOGE(TAG, "Socket unable to bind: errno %d", errno);
    goto CLEAN_UP;
  }
  ESP_LOGI(TAG, "Socket bound, port %d", PORT);

  // 4. Start Listening for connections
  err = listen(listen_sock, 1); // Allow 1 pending connection
  if (err != 0) {
    ESP_LOGE(TAG, "Error occurred during listen: errno %d", errno);
    goto CLEAN_UP;
  }
  ESP_LOGI(TAG, "Socket listening");

  while (1) {
    // 5. Accept Client Connection
    ESP_LOGI(TAG, "Waiting for new client connection...");
    struct sockaddr_storage source_addr;
    socklen_t addr_len = sizeof(source_addr);
    int client_sock =
        accept(listen_sock, (struct sockaddr *)&source_addr, &addr_len);

    if (client_sock < 0) {
      ESP_LOGE(TAG, "Unable to accept connection: errno %d", errno);
      continue;
    }

    // Log the connected client IP
    if (source_addr.ss_family == AF_INET) {
      inet_ntoa_r(((struct sockaddr_in *)&source_addr)->sin_addr, addr_str,
                  sizeof(addr_str) - 1);
    }
    ESP_LOGI(TAG, "Client connected from %s", addr_str);

    // 6. Communication Loop
    int len;
    do {
      // Receive data from the client
      len = recv(client_sock, rx_buffer, sizeof(rx_buffer) - 1, 0);

      if (len > 0) {
        // Null-terminate the received data for string processing
        rx_buffer[len] = 0;

        // Process the command using the external handler
        handle_command(client_sock, rx_buffer);
      }
    } while (len > 0); // Loop continues as long as data is received

    // 7. Clean up connection when loop exits (client disconnects)
    if (len < 0) {
      ESP_LOGE(TAG, "Error occurred during receive: errno %d", errno);
    }

    // Close the client socket and log the disconnection
    shutdown(client_sock, 0);
    close(client_sock);
    ESP_LOGI(TAG, "Client disconnected, socket closed");
  }

CLEAN_UP:
  if (listen_sock != -1) {
    close(listen_sock);
  }
  vTaskDelete(NULL);
}

static void wifi_event_handler(void *arg, esp_event_base_t event_base,
                               int32_t event_id, void *event_data) {
  if (event_id == WIFI_EVENT_AP_STACONNECTED) {
    wifi_event_ap_staconnected_t *event =
        (wifi_event_ap_staconnected_t *)event_data;
    ESP_LOGI(TAG, "station " MACSTR " join, AID=%d", MAC2STR(event->mac),
             event->aid);
  } else if (event_id == WIFI_EVENT_AP_STADISCONNECTED) {
    wifi_event_ap_stadisconnected_t *event =
        (wifi_event_ap_stadisconnected_t *)event_data;
    ESP_LOGI(TAG, "station " MACSTR " leave, AID=%d, reason=%d",
             MAC2STR(event->mac), event->aid, event->reason);
  }
}

void wifi_init_softap(void) {
  ESP_ERROR_CHECK(esp_netif_init());
  ESP_ERROR_CHECK(esp_event_loop_create_default());
  esp_netif_create_default_wifi_ap();

  wifi_init_config_t cfg = WIFI_INIT_CONFIG_DEFAULT();
  ESP_ERROR_CHECK(esp_wifi_init(&cfg));

  ESP_ERROR_CHECK(esp_event_handler_instance_register(
      WIFI_EVENT, ESP_EVENT_ANY_ID, &wifi_event_handler, NULL, NULL));

  wifi_config_t wifi_config = {
      .ap =
          {
              .ssid = EXAMPLE_ESP_WIFI_SSID,
              .ssid_len = strlen(EXAMPLE_ESP_WIFI_SSID),
              .channel = EXAMPLE_ESP_WIFI_CHANNEL,
              .password = EXAMPLE_ESP_WIFI_PASS,
              .max_connection = EXAMPLE_MAX_STA_CONN,
              .authmode = WIFI_AUTH_WPA2_PSK,
              .pmf_cfg =
                  {
                      .required = true,
                  },
              //              .gtk_rekey_interval = 600,
          },
  };
  if (strlen(EXAMPLE_ESP_WIFI_PASS) == 0) {
    wifi_config.ap.authmode = WIFI_AUTH_OPEN;
  }

  ESP_ERROR_CHECK(esp_wifi_set_mode(WIFI_MODE_AP));
  ESP_ERROR_CHECK(esp_wifi_set_config(WIFI_IF_AP, &wifi_config));
  ESP_ERROR_CHECK(esp_wifi_start());

  ESP_LOGI(TAG, "wifi_init_softap finished. SSID:%s password:%s channel:%d",
           EXAMPLE_ESP_WIFI_SSID, EXAMPLE_ESP_WIFI_PASS,
           EXAMPLE_ESP_WIFI_CHANNEL);
}

void app_main(void) {
  // Initialize NVS
  esp_err_t ret = nvs_flash_init();
  if (ret == ESP_ERR_NVS_NO_FREE_PAGES ||
      ret == ESP_ERR_NVS_NEW_VERSION_FOUND) {
    ESP_ERROR_CHECK(nvs_flash_erase());
    ret = nvs_flash_init();
  }
  ESP_ERROR_CHECK(ret);

  ESP_LOGI(TAG, "ESP_WIFI_MODE_AP");
  wifi_init_softap();
  xTaskCreate(oven_simulator_task, "oven_sim", 4096, NULL, 5, NULL);
  xTaskCreate(tcp_server_task, "tcp_server", 4096, NULL, 5, NULL);
}
