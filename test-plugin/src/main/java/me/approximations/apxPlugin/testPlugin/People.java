package me.approximations.apxPlugin.testPlugin;

import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;


@Getter
@Setter
@NoArgsConstructor(force = true)
@AllArgsConstructor
@ToString
@Entity
@Table(name = "jpa_peoples")
public class People implements Serializable {
    @Id
    @GeneratedValue(generator = "uuid-hibernate-generator")
    @GenericGenerator(name = "uuid-hibernate-generator", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "uuid", updatable = false, nullable = false, columnDefinition = "VARCHAR(36)")
    private final UUID uuid;
    private String name;
    private String email;
    @Column(name = "created_at")
    private final Instant createdAt;
}
