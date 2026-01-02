package com.ilja.smarthome.energycontrol.repository;

import com.ilja.smarthome.energycontrol.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for User entities.
 * Provides data access methods for user authentication and management.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find a user by username.
     * Used for authentication.
     */
    Optional<User> findByUsername(String username);

    /**
     * Find a user by email.
     */
    Optional<User> findByEmail(String email);

    /**
     * Check if a username exists.
     */
    boolean existsByUsername(String username);

    /**
     * Check if an email exists.
     */
    boolean existsByEmail(String email);
}
