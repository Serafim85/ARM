package com.networkscanner.backend.dashboards.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("GRAPH")
public class GraphWidgetEntity extends AbstractWidgetEntity {
}
