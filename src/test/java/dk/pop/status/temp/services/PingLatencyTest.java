package dk.pop.status.temp.services;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PingLatencyTest {
    @Test
    void parsesLinuxPingRoundTripTime() {
        assertThat(StatusMonitoringService.pingLatencyMs("64 bytes from host: icmp_seq=1 ttl=64 time=12.6 ms"))
                .isEqualTo(13);
    }

    @Test
    void parsesWindowsPingRoundTripTime() {
        assertThat(StatusMonitoringService.pingLatencyMs("Reply from host: bytes=32 time=4ms TTL=128"))
                .isEqualTo(4);
    }

    @Test
    void treatsSubMillisecondPingAsOneMillisecond() {
        assertThat(StatusMonitoringService.pingLatencyMs("Reply from host: bytes=32 time<1ms TTL=128"))
                .isEqualTo(1);
    }

    @Test
    void returnsNullWhenPingOutputHasNoRoundTripTime() {
        assertThat(StatusMonitoringService.pingLatencyMs("Request timed out.")).isNull();
    }
}
