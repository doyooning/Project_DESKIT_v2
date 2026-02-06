package com.deskit.deskit.account.address.repository;

import com.deskit.deskit.account.address.entity.Address;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AddressRepository extends JpaRepository<Address, Long> {

  long countByMemberId(Long memberId);

  List<Address> findByMemberIdOrderByIsDefaultDescIdDesc(Long memberId);

  boolean existsByMemberIdAndIsDefaultTrue(Long memberId);

  Optional<Address> findByIdAndMemberId(Long id, Long memberId);

  Optional<Address> findByMemberIdAndIsDefaultTrue(Long memberId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("update Address a set a.isDefault = false where a.memberId = :memberId and a.isDefault = true")
  int clearDefaultByMemberId(@Param("memberId") Long memberId);
}
