package com.jredis.components.services;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResponseDto {
    private String response;
    private byte[] data;

    public ResponseDto(String response) {
        this.response = response;
    }
}
