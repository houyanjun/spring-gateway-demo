package com.example.demo.aop;

import com.example.demo.api.dto.DemoDTO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RpcController {

    @PostMapping(value="/*")

    public DemoDTO hello() {
        DemoDTO demoDTO = new DemoDTO();
        demoDTO.setName("hou1");
        return demoDTO;
    }
}
