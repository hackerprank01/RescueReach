package com.rescuereach.data.repository;

public interface OnCompleteListener {
    void onSuccess();
    void onError(Exception e);
}