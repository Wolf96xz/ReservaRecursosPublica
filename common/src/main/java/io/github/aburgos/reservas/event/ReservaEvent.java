package io.github.aburgos.reservas.event;

public sealed interface ReservaEvent permits ReservaCreada, ReservaCancelada {
}
