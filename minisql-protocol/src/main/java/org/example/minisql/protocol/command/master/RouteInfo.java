package org.example.minisql.protocol.command.master;

public record RouteInfo(
    String tableName,
    String primaryRegion,
    String secondaryRegion
) {
    public boolean hasSecondaryRegion() {
        return secondaryRegion != null && !secondaryRegion.isBlank() && !"-".equals(secondaryRegion);
    }
}
