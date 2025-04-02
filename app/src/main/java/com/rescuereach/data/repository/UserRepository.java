package com.rescuereach.data.repository;

import com.rescuereach.data.model.User;

import java.util.List;

public interface UserRepository {
    void getUserById(String userId, OnUserFetchedListener listener);
    void getUserByPhoneNumber(String phoneNumber, OnUserFetchedListener listener);
    void saveUser(User user, OnCompleteListener listener);
    void updateUserProfile(User user, OnCompleteListener listener); // Updated method
    void deleteUser(String phoneNumber, OnCompleteListener listener); // Changed from userId to phoneNumber
    void getAllUsers(OnUserListFetchedListener listener);

    interface OnUserFetchedListener {
        void onSuccess(User user);
        void onError(Exception e);
    }

    interface OnUserListFetchedListener {
        void onSuccess(List<User> users);
        void onError(Exception e);
    }
}