package com.Shubham.carDealership.service;

import com.Shubham.carDealership.dto.UserDto;
import com.Shubham.carDealership.model.User;
import com.Shubham.carDealership.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    public UserDto getUserById(Long id) {
        Optional<User> user = userRepository.findById(id);
        if (user.isPresent()) {
            UserDto userDto = new UserDto();
            userDto.setId(user.get().getId());
            userDto.setUsername(user.get().getUsername());
            userDto.setEmail(user.get().getEmail());
            userDto.setRole(user.get().getRole());
            userDto.setPhoneNumber(user.get().getPhoneNumber()); // NEW
            return userDto;
        }
        return null;
    }

    public UserDto getUserByEmail(String email) {
        Optional<User> user = userRepository.findByEmail(email);
        if (user.isPresent()) {
            UserDto userDto = new UserDto();
            userDto.setId(user.get().getId());
            userDto.setUsername(user.get().getUsername());
            userDto.setEmail(user.get().getEmail());
            userDto.setRole(user.get().getRole());
            userDto.setPhoneNumber(user.get().getPhoneNumber()); // NEW
            return userDto;
        }
        return null;
    }
}