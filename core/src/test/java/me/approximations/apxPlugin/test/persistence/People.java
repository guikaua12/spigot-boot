//package me.approximations.apxPlugin.test.persistence;
//
//import lombok.*;
//
//import javax.persistence.*;
//import java.time.Instant;
//
//@Getter
//@Setter
//@NoArgsConstructor(force = true)
//@EqualsAndHashCode(onlyExplicitlyIncluded = true)
//@AllArgsConstructor
//@ToString
//@DatabaseTable(tableName = "person")
//public class People {
//    @EqualsAndHashCode.Include
//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
//    private final Long id;
//    private String name;
//    private String email;
//    @Column(name = "created_at")
//    private final Instant createdAt;
//}