package com.networkscanner.backend.dashboards.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("PLACEHOLDER")
public class PlaceholderWidgetEntity extends AbstractWidgetEntity {
}
