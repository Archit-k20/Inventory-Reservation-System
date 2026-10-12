package com.archit.inventory.demo;

import jakarta.validation.constraints.Size;

public record DemoOrderFailureRequest(@Size(max = 255) String reason) { }
