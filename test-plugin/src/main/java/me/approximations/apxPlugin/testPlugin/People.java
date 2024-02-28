package me.approximations.apxPlugin.testPlugin;

import lombok.*;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.Serializable;
import java.time.Instant;


@Getter
@Setter
@NoArgsConstructor(force=true)
@AllArgsConstructor
@ToString
@Entity
@Table(name="jpa_peoples")
public class People implements Serializable {
    @Id
    private final String uuid;
    private String name;
    private String email;
    @Column(name="created_at") private final Instant createdAt;
}
