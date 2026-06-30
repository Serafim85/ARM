package com.networkscanner.backend.dashboards.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("CLOCK")
public class ClockWidgetEntity extends AbstractWidgetEntity {
}
