# Flujo de reserva del cliente

```mermaid
flowchart TD
    A([Cliente entra al link del local]) --> B[Elige uno o más servicios]
    B --> C[Elige profesional]
    C --> D[Elige día]
    D --> E[Elige hora libre]
    E --> AP[El horario queda apartado unos minutos]
    AP --> F[/Deja nombre, teléfono y mail opcional/]
    F --> G[Toca Confirmar]
    G --> V{¿Ese teléfono ya tiene 2 turnos futuros?}
    H --> I[Mensaje al cliente con link para cancelar o reprogramar]
    H --> J[Notificación en el panel del profesional]
    I --> K([Turno reservado])
    J --> K
    V -- Sí --> X[Mensaje: ya tenés 2 turnos reservados]
    X --> Z([Fin sin reservar])
    V -- No --> S{¿La hora sigue libre?}
    S -- No --> O[Mensaje: alguien reservó esa hora recién o venció el apartado]
    O --> E
    S -- Sí --> H[Se guarda el turno]
```

En la v1 el mensaje al cliente sale por un adaptador simulado; el envío real por WhatsApp queda preparado pero apagado. La pantalla de confirmación muestra el link de "Mi turno".
