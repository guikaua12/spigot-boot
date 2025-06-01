package me.approximations.apxPlugin.testPlugin;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import lombok.*;
import me.approximations.apxPlugin.data.ormLite.persisters.InstantPersister;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;


@Getter
@Setter
@NoArgsConstructor(force = true)
@AllArgsConstructor
@ToString
@DatabaseTable(tableName = "person")
public class People implements Serializable {
    @DatabaseField(generatedId = true, columnName = "id", canBeNull = false, dataType = DataType.UUID)
    private final UUID uuid;
    @DatabaseField(columnName = "name", canBeNull = false)
    private String name;
    @DatabaseField(columnName = "email", canBeNull = false)
    private String email;
    @DatabaseField(columnName = "created_at", canBeNull = false, persisterClass = InstantPersister.class)
    private final Instant createdAt;
}
