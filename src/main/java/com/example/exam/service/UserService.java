package com.example.exam.service;

import com.example.exam.model.User;

public interface UserService {
    User saveStudent(User user);

    User findByUsername(String username);
}
