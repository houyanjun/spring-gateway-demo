package com.example.demogateway.api;

import com.example.demogateway.api.dto.DemoDTO;
import com.example.demogateway.config.SfconfigProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/gateway/hello")
public class HelloApiService {

    @Autowired
    private SfconfigProperties sfconfigProperties;

    @PostMapping(value="/test")
    public DemoDTO hello() {
        DemoDTO demoDTO = new DemoDTO();
        demoDTO.setName("hou");
        System.out.println(sfconfigProperties.getName());
        return demoDTO;
    }
}
