package com.wheregoes.camping;

import android.os.Binder;

class CampingBinder extends Binder {
    private final CampingService service;

    CampingBinder(CampingService service) {
        this.service = service;
    }

    CampingService getService() { return service; }
}
