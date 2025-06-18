package me.approximations.spigotboot.messaging.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor(force=true)
@Data
public class MessageResponse<T extends Serializable> implements Serializable {
    private final UUID responseId;
    private final T body;
}
