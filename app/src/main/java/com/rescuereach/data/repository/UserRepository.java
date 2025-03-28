package com.rescuereach.data.repository;

import com.rescuereach.data.model.User;

import java.util.List;

public interface UserRepository {
    interface OnUserFetchedListener {
        void onSuccess(User user);
        void onError(Exception e);
    }

    interface OnUserListFetchedListener {
        void onSuccess(List<User> users);
        void onError(Exception e);
    }

    void getUserById(String userId, OnUserFetchedListener listener);
    void getUserByPhoneNumber(String phoneNumber, OnUserFetchedListener listener);
    void saveUser(User user, OnCompleteListener listener);
    void updateUser(User user, OnCompleteListener listener);
    void deleteUser(String userId, OnCompleteListener listener);
    void getAllUsers(OnUserListFetchedListener listener);
}