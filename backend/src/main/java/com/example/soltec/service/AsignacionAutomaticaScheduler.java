package com.example.soltec.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// CU-04 v2: los triggers de la base ya intentan asignar cada caso apenas
// entra a la bolsa o apenas se libera capacidad. Esta tarea es la red de
// seguridad para lo que quedo pendiente porque no habia tecnico disponible
// en ese momento (fixedDelay evita que dos corridas se superpongan si una
// corrida tarda mas de 5 minutos).
@Component
public class AsignacionAutomaticaScheduler {

    private static final Logger log = LoggerFactory.getLogger(AsignacionAutomaticaScheduler.class);
    private static final long CADA_5_MINUTOS = 5 * 60 * 1000L;

    @PersistenceContext
    private EntityManager entityManager;

    @Scheduled(fixedDelay = CADA_5_MINUTOS)
    @Transactional
    public void procesarBolsaDeDemanda() {
        Number asignados = (Number) entityManager
                .createNativeQuery("SELECT soltec.fn_procesar_bolsa()")
                .getSingleResult();
        log.info("fn_procesar_bolsa: {} caso(s) asignado(s) en esta corrida", asignados.intValue());
    }
}
