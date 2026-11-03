package com.grocery.platform.repository;

import com.grocery.platform.entity.Banner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BannerRepository extends JpaRepository<Banner, Long> {

    /** Active banners in page order; the time window is checked in Java (Banner.isLiveAt). */
    List<Banner> findByActiveTrueOrderByPositionAscIdAsc();

    List<Banner> findAllByOrderByPositionAscIdAsc();
}
