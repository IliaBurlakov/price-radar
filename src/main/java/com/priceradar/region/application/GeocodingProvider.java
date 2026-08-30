package com.priceradar.region.application;

public interface GeocodingProvider {

    GeocodingResult search(String query);
}
