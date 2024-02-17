package me.approximations.apxPlugin.testPlugin;

import lombok.*;

import javax.persistence.*;
import java.time.Instant;


@Getter
@Setter
@NoArgsConstructor(force=true)
@AllArgsConstructor
@ToString
@Entity
@Table(name="jpa_peoples")
public class People {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private final Long id;
    private String name;
    private String email;
    @Column(name="created_at") private final Instant createdAt;
}
