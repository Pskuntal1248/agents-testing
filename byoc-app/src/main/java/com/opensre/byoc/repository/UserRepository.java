package com.opensre.byoc.repository;

import com.opensre.byoc.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
