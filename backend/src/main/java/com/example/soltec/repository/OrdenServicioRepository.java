package com.example.soltec.repository;

import com.example.soltec.entity.OrdenServicio;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrdenServicioRepository extends JpaRepository<OrdenServicio, Integer> {

    // Usa la vista vw_ordenes_cliente: ya viene filtrada a columnas seguras
    // para el cliente (sin tecnico) y ordenada para el desplegable.
    @Query(value = """
            SELECT orden_id AS ordenId, numero_orden AS numeroOrden, servicio,
                   fecha_servicio AS fechaServicio, descripcion
            FROM soltec.vw_ordenes_cliente
            WHERE cliente_id = :clienteId
            ORDER BY fecha_servicio DESC
            """, nativeQuery = true)
    List<OrdenClienteProyeccion> buscarPorCliente(@Param("clienteId") Integer clienteId);
}
