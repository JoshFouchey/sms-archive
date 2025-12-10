package com.joshfouchey.smsarchive.repository;

import com.joshfouchey.smsarchive.model.Contact;
import com.joshfouchey.smsarchive.model.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ContactRepository extends JpaRepository<Contact, Long> {

    // Single lookup
    Optional<Contact> findByNormalizedNumber(String normalizedNumber);

    // Batch lookup (optional helper)
    List<Contact> findByNormalizedNumberIn(Collection<String> normalizedNumbers);

    // added user-scoped methods
    @Query("select c from Contact c where c.user = :user and (c.isArchived = false or c.isArchived is null)")
    List<Contact> findAllByUser(@Param("user") User user);

    @Query("select c from Contact c where c.user = :user and c.normalizedNumber = :norm and (c.isArchived = false or c.isArchived is null)")
    Optional<Contact> findByUserAndNormalizedNumber(@Param("user") User user, @Param("norm") String normalizedNumber);
}