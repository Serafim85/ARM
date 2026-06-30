package com.networkscanner.backend.network.scan.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IpRangeParserTest {

  private IpRangeParser parser;

  @BeforeEach
  void setUp() {
    parser = new IpRangeParser();
  }

  @Test
  void normalizeSubnetRange_acceptsLastOctetRange() {
    assertThat(parser.normalizeSubnetRange("192.168.176.0-255")).isEqualTo("192.168.176.0-255");
  }

  @Test
  void normalizeSubnetRange_convertsSlash24Cidr() {
    assertThat(parser.normalizeSubnetRange("192.168.1.0/24")).isEqualTo("192.168.1.0-255");
  }

  @Test
  void normalizeSubnetRange_convertsSlash25Cidr() {
    assertThat(parser.normalizeSubnetRange("192.168.1.128/25")).isEqualTo("192.168.1.128-255");
  }

  @Test
  void expandRange_skipsNetworkAndBroadcastHosts() {
    List<String> addresses = parser.expandRange("192.168.1.0/24");

    assertThat(addresses).hasSize(254);
    assertThat(addresses.get(0)).isEqualTo("192.168.1.1");
    assertThat(addresses.get(addresses.size() - 1)).isEqualTo("192.168.1.254");
  }

  @Test
  void normalizeSubnetRange_rejectsInvalidInput() {
    assertThatThrownBy(() -> parser.normalizeSubnetRange("not-a-subnet"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(IpRangeParser.SUBNET_FORMAT_ERROR);
    assertThatThrownBy(() -> parser.normalizeSubnetRange("192.168.1.0/23"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(IpRangeParser.SUBNET_SCOPE_ERROR);
    assertThatThrownBy(() -> parser.normalizeSubnetRange("10.0.0.0/20"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(IpRangeParser.SUBNET_SCOPE_ERROR);
  }
}
