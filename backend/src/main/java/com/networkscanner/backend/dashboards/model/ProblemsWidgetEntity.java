package com.networkscanner.backend.dashboards.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("PROBLEMS")
public class ProblemsWidgetEntity extends AbstractWidgetEntity {
}
