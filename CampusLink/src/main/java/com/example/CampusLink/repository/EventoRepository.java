package com.example.CampusLink.repository;

import com.example.CampusLink.model.Evento;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoRepository extends JpaRepository<Evento, Long> {
}