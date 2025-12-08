import socket
import threading
import time
from typing import Dict, Any

# --- Configuration ---
HOST = '0.0.0.0'  # Listen on all available interfaces
PORT = 8080       # Port the server will listen on
TAG = "[SIMULATOR]"

# --- Global Oven State (Shared across threads) ---
oven_state: Dict[str, Any] = {
    "current_temp": 25.0,
    "target_temp": 0.0,
    "status": "IDLE",
    "timer": 0,
    "profile": {"soak_temp": 0.0, "soak_time": 0, "reflow_temp": 0.0, "reflow_time": 0}
}
lock = threading.Lock() # Lock for thread-safe access to oven_state

# --- Simulation Constants ---
HEAT_RATE = 1.0  # Temperature rise per second
COOL_RATE = 0.5  # Temperature drop per second

# --- Protocol Functions ---

def generate_status_response() -> str:
    """Generates the STATUS response string based on the global state."""
    with lock:
        temp = oven_state["current_temp"]
        target = oven_state["target_temp"]
        status = oven_state["status"]
        timer = oven_state["timer"]
        
        # Protocol: STATUS;CurrentTemp;TargetTemp;OvenStatus;Timer\n
        return f"STATUS;{temp:.1f};{target:.1f};{status};{timer}\n"

def parse_start_command(command: str) -> bool:
    """Parses the START command and updates the oven state."""
    try:
        # Expected: START;150;60;220;30\n
        parts = command.split(';')
        if len(parts) < 5:
            print(f"{TAG} ERROR: START command missing parameters: {command}")
            return False

        soak_temp = float(parts[1])
        soak_time = int(parts[2])
        reflow_temp = float(parts[3])
        reflow_time = int(parts[4].split('\n')[0]) # Strip newline
        
        with lock:
            oven_state["profile"] = {
                "soak_temp": soak_temp, 
                "soak_time": soak_time, 
                "reflow_temp": reflow_temp, 
                "reflow_time": reflow_time
            }
            oven_state["target_temp"] = soak_temp
            oven_state["status"] = "HEATING"
            oven_state["timer"] = 0
            print(f"{TAG} START command received. Targeting soak temp: {soak_temp}°C")
        return True

    except Exception as e:
        print(f"{TAG} ERROR parsing START command: {e}")
        return False

# --- Oven Simulation Task (Runs in its own thread) ---

def oven_simulator_loop():
    """Simulates temperature changes and reflow stage transitions."""
    print(f"{TAG} Oven simulation started.")
    
    while True:
        with lock:
            status = oven_state["status"]
            current_temp = oven_state["current_temp"]
            target_temp = oven_state["target_temp"]
            profile = oven_state["profile"]

            # Heating Logic
            if status in ["HEATING", "SOAKING", "REFLOWING"]:
                if current_temp < target_temp:
                    # Heating phase
                    oven_state["current_temp"] += HEAT_RATE
                    if oven_state["current_temp"] >= target_temp:
                        oven_state["current_temp"] = target_temp # Clamp temp
                        
                        # Transition to next stage
                        if status == "HEATING":
                            oven_state["status"] = "SOAKING"
                            oven_state["timer"] = profile["soak_time"] # Start soak countdown
                            print(f"{TAG} Transition: SOAKING started.")
                        elif status == "REFLOWING":
                            oven_state["status"] = "COOLING"
                            oven_state["target_temp"] = 25.0 # Target room temp
                            oven_state["timer"] = 0
                            print(f"{TAG} Transition: COOLING started.")
                        # If already SOAKING, it means it hit the target, next state is handled by timer

            # Timer-based Transitions (Only for SOAKING in this simplified model)
            if status == "SOAKING":
                oven_state["timer"] -= 1
                if oven_state["timer"] <= 0:
                    # Time to ramp to reflow peak
                    oven_state["status"] = "REFLOWING"
                    oven_state["target_temp"] = profile["reflow_temp"]
                    oven_state["timer"] = profile["reflow_time"]
                    print(f"{TAG} Transition: REFLOWING started.")
            
            # Cooling Logic
            elif status == "COOLING":
                if current_temp > 25.0:
                    oven_state["current_temp"] -= COOL_RATE
                else:
                    oven_state["current_temp"] = 25.0
                    oven_state["status"] = "COMPLETE"
                    oven_state["timer"] = 0
                    print(f"{TAG} Cycle COMPLETE.")
            
            # IDLE or COMPLETE state
            elif status in ["IDLE", "COMPLETE", "ERROR"]:
                # Do nothing, just maintain current temp
                pass
            
            # Print status every 5 seconds for visual verification
            if oven_state["timer"] % 5 == 0:
                 print(f"{TAG} SIMULATION: {status} | Temp: {current_temp:.1f}°C")

        time.sleep(1) # Simulation step every second

# --- Client Handler (Runs for each connected Android client) ---

def handle_client(client_socket: socket.socket, client_address: tuple):
    """Handles communication with a single client."""
    print(f"{TAG} New connection from {client_address}")
    
    # Send an initial status message upon connection
    client_socket.sendall(generate_status_response().encode('utf-8'))
    
    try:
        # Use a small buffer to handle partial messages and commands
        buffer = ""
        while True:
            data = client_socket.recv(1024).decode('utf-8')
            if not data:
                break # Client closed the connection
            
            buffer += data
            
            # Process complete commands (terminated by \n)
            while '\n' in buffer:
                command, buffer = buffer.split('\n', 1)
                command = command.strip()
                
                if not command:
                    continue

                if command == "STATUS?":
                    response = generate_status_response()
                    client_socket.sendall(response.encode('utf-8'))
                
                elif command.startswith("START"):
                    if parse_start_command(command + '\n'): # Re-add \n for unified parsing
                        client_socket.sendall(generate_status_response().encode('utf-8'))
                
                elif command == "STOP":
                    with lock:
                        oven_state["status"] = "COOLING"
                        oven_state["target_temp"] = 25.0
                        oven_state["timer"] = 0
                        print(f"{TAG} STOP command received. Starting COOLING.")
                    client_socket.sendall(generate_status_response().encode('utf-8'))
                
                # Add logic for PROFILE command here if needed
                
                else:
                    print(f"{TAG} Unknown command received: {command}")

    except ConnectionResetError:
        print(f"{TAG} Client {client_address} forcibly closed the connection.")
    except Exception as e:
        print(f"{TAG} Error handling client {client_address}: {e}")
    finally:
        client_socket.close()
        print(f"{TAG} Connection with {client_address} closed.")

# --- Main Server Function ---

def start_server():
    """Sets up the TCP listening socket and starts the simulation thread."""
    
    # Start the simulation thread
    simulation_thread = threading.Thread(target=oven_simulator_loop, daemon=True)
    simulation_thread.start()
    
    # Setup TCP Server
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        try:
            s.bind((HOST, PORT))
            s.listen()
            print(f"\n{TAG} *** Python Reflow Server Simulation Started ***")
            print(f"{TAG} Listening on {HOST}:{PORT}")
            print(f"{TAG} Waiting for Android client to connect...\n")

            while True:
                conn, addr = s.accept()
                # Start a new thread to handle the client connection
                client_thread = threading.Thread(target=handle_client, args=(conn, addr))
                client_thread.start()
        
        except OSError as e:
            if e.errno == 98:
                 print(f"{TAG} ERROR: Port {PORT} is already in use. Please close other applications or change the PORT variable.")
            else:
                 print(f"{TAG} An unexpected OSError occurred: {e}")
        except Exception as e:
            print(f"{TAG} Server crashed: {e}")

if __name__ == "__main__":
    start_server()
