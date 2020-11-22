package com.krillsson.sysapi.core.metrics.windows;

import com.krillsson.sysapi.core.domain.drives.DriveSpeed;
import com.krillsson.sysapi.core.domain.drives.OsPartition;
import com.krillsson.sysapi.core.speed.SpeedMeasurementManager;
import ohmwrapper.DriveMonitor;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import oshi.hardware.HWDiskStore;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OperatingSystem;

import java.util.Optional;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

@Ignore("Currently broken because of UnsatisfiedLinkerError")
public class WindowsDiskProviderTest {

    DelegatingOHMManager monitorManager;

    WindowsDriveMetrics diskProvider;
    private HWDiskStore store;
    private OsPartition osFileStore;
    private DriveMonitor driveMonitor;

    @Before
    public void setUp() throws Exception {
        monitorManager = mock(DelegatingOHMManager.class);
        store = mock(HWDiskStore.class);
        osFileStore = mock(OsPartition.class);
        driveMonitor = mock(DriveMonitor.class);

        diskProvider = new WindowsDriveMetrics(
                mock(OperatingSystem.class),
                mock(HardwareAbstractionLayer.class),
                mock(SpeedMeasurementManager.class)
        );
        diskProvider.setMonitorManager(monitorManager);
    }

    @Test
    public void happyPath() {
        when(monitorManager.DriveMonitors()).thenReturn(new DriveMonitor[]{driveMonitor});
        when(osFileStore.getMount()).thenReturn("C:/");
        when(driveMonitor.getLogicalName()).thenReturn("C:/");
        when(driveMonitor.getWriteRate()).thenReturn(123d);
        when(driveMonitor.getReadRate()).thenReturn(321d);
        Optional<DriveSpeed> diskSpeedOptional = diskProvider.diskSpeedForStore(store, osFileStore);
        DriveSpeed driveSpeed = diskSpeedOptional.get();
        assertThat(driveSpeed.getWriteBytesPerSecond(), is(123L));
        assertThat(driveSpeed.getReadBytesPerSecond(), is(321L));
        verify(monitorManager).update();
    }

    @Test
    public void nullReturnsDefaultValues() {
        Optional<DriveSpeed> diskSpeedOptional = diskProvider.diskSpeedForStore(null, null);
        DriveSpeed driveSpeed = diskSpeedOptional.get();
        assertThat(driveSpeed.getReadBytesPerSecond(), is(0L));
        assertThat(driveSpeed.getWriteBytesPerSecond(), is(0L));
    }
}