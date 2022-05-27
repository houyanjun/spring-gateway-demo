package com.example.demo.api.dto;

import java.io.Serializable;

public class DemoDTO implements Serializable {
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
