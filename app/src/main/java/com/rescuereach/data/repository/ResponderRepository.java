package com.rescuereach.data.repository;

import com.rescuereach.data.model.Responder;

import java.util.List;

public interface ResponderRepository {
    interface OnResponderFetchedListener {
        void onSuccess(Responder responder);
        void onError(Exception e);
    }

    interface OnResponderListFetchedListener {
        void onSuccess(List<Responder> responders);
        void onError(Exception e);
    }

    void getResponderById(String responderId, OnResponderFetchedListener listener);
    void getResponderByUsername(String username, OnResponderFetchedListener listener);
    void getRespondersByRole(Responder.ResponderRole role, OnResponderListFetchedListener listener);
    void getAllResponders(OnResponderListFetchedListener listener);
    void saveResponder(Responder responder, OnCompleteListener listener);
    void updateResponder(Responder responder, OnCompleteListener listener);
    void deleteResponder(String responderId, OnCompleteListener listener);
}