package com.topsiya.riot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountDto(String puuid, String gameName, String tagLine) {
}
