package com.example.reflowoven

import org.junit.Test

import org.junit.Assert.*

import com.example.reflowoven.data.model.OvenState
import com.example.reflowoven.data.model.ReflowProfile
import com.example.reflowoven.data.repository.ReflowOvenRepository
import com.example.reflowoven.ui.viewmodel.MainViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private lateinit var viewModel: MainViewModel
    private lateinit var repository: ReflowOvenRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        repository = mockk(relaxed = true)

        viewModel = MainViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Check if graph data is cleared when startOven is called as it is a new process
    @Test
    fun data_graph_rst_start() = runTest {
        coEvery { repository.connect(any(), any()) } returns flowOf(true)
        coEvery { repository.getOvenState() } returns flowOf(
            OvenState(currentTemperature = 25f),
            OvenState(currentTemperature = 30f)
        )

        viewModel.connect("192.168.1.50", 8080)
        advanceUntilIdle()

        assertTrue(viewModel.tempHistory.value.isNotEmpty())
        assertEquals(2, viewModel.tempHistory.value.size)

        val dummyProfile = ReflowProfile("Test", emptyList())
        viewModel.startOven(dummyProfile)
        advanceUntilIdle()

        assertEquals(0, viewModel.tempHistory.value.size)

        coVerify { repository.startOven(dummyProfile) }
    }


    // Check if incoming data changes current temperature shown
    @Test
    fun check_incoming() = runTest {
        val expectedTemp = 150.5f
        coEvery { repository.connect(any(), any()) } returns flowOf(true)
        coEvery { repository.getOvenState() } returns flowOf(
            OvenState(currentTemperature = expectedTemp, status = "Reflow")
        )

        viewModel.connect("1.2.3.4", 9999)
        advanceUntilIdle()

        assertEquals(expectedTemp, viewModel.ovenState.value.currentTemperature)
        assertEquals("Reflow", viewModel.ovenState.value.status)
    }
}