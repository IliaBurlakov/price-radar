package com.priceradar.sharedbasket.application;

public interface SharedBasketProvider {

    SharedBasketProviderResult fetch(String shareId);
}
