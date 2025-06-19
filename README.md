# Spigot Boot

Spigot Boot is the most complete library inspired by Spring Boot, designed specifically for Spigot plugins. It provides:

- Robust Dependency Injection for managing your plugin's components
- An IOC container that manages dependencies and method intercepts (beans)
- A data module featuring OrmLite with automatic Transaction Management
- A Placeholder module that integrates with [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) to
  automatically handles placeholders
- An improved API for Plugin Messaging Channel, making BungeeCord and Spigot communication easier

With Spigot Boot, you can build modular, maintainable, and scalable Spigot plugins with ease.

## Getting Started

To get started, you need to add the dependency to your project.

**Maven**:

```xml

<dependency>
    <groupId>tech.guilhermekaua.spigot-boot</groupId>
    <artifactId>spigot-boot-core</artifactId>
    <version>2.0.2-SNAPSHOT</version>
    <scope>compile</scope>
</dependency>
```

**Gradle**:

```groovy
dependencies {
    compileOnly 'tech.guilhermekaua.spigot-boot:spigot-boot-core:2.0.2-SNAPSHOT'
}
```

> [!IMPORTANT]  
> Spigot Boot does not use any library part of the Spring Framework, i just took inspiration from Spring Boot to
> implement everything from scratch.
> This means that you don't need to add any additional dependencies to your project, and also means the disk size of the
`core` module is very small, around 980KB.

## Dependency Injection

Spigot Boot provides a Dependency Injection system that allows you to manage your plugin's components easily.
3 types of injections are available:

- **Field Injection**: Use the `@Inject` annotation to inject dependencies into fields.
- **Method Injection**: Use the `@Inject` annotation on methods to inject dependencies.
- **Constructor Injection**: You don't need to use any annotation, just declare the constructor with the dependencies as
  parameters.

```java
// Example of Field Injection
@Component
public class MyClass {
    @Inject
    private MyDependency myDependency;
}

// Example of Method Injection
@Component
public class MyClass {
    private MyDependency myDependency;

    @Inject
    public void setMyDependency(MyDependency myDependency) {
        this.myDependency = myDependency;
    }
}

// Example of Constructor Injection
@Component
public class MyClass {
    private final MyDependency myDependency;

    public MyClass(MyDependency myDependency) {
        this.myDependency = myDependency;
    }
}
```

## Plugin Annotation Processor

Spigot Boot provides a Plugin Annotation Processor that, when compiling your code, automatically creates a `plugin.yml`
file with the data provided
on the @Plugin annotation.
You can use the `@Plugin` annotation to define your plugin's metadata, such as name, version, main class, and
dependencies.

```java

@Plugin(
        name = "TestPlugin",
        version = "1.0.0",
        description = "A test plugin for ApxPlugin framework.",
        authors = {"Approximations"}
)
public class MyPlugin extends ApxPlugin {
    // ...
}
```

## Placeholder module

The Placeholder module integrates with [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) to
automatically handle placeholders in your plugin. You can use the `@Placeholder` annotation to define placeholders in
your
plugin.

First, you need to enable the Placeholder module in your dependencies:

```xml

<dependency>
    <groupId>tech.guilhermekaua.spigot-boot</groupId>
    <artifactId>spigot-boot-placeholder</artifactId>
    <version>2.0.2-SNAPSHOT</version>
    <scope>compile</scope>
</dependency>
```

Then, you can define placeholders in your plugin using the `@RegisterPlaceholder` and `@Placeholder` annotations:

```java
import org.bukkit.entity.Player;
import me.approximations.spigotboot.placeholder.annotations.Param;
import me.approximations.spigotboot.placeholder.annotations.Placeholder;
import me.approximations.spigotboot.placeholder.annotations.RegisterPlaceholder;

@RequiredArgsConstructor
@RegisterPlaceholder
public class Placeholders {
    private final UserService userService;

    @Placeholder(
            value = "user_name",
            description = "Returns the name of the user associated with the player."
    )
    public String userNamePlaceholder(Player player) {
        return userService.getUser(player.getUniqueId())
                .map(User::getName)
                .orElse("User not found!");
    }

    // Placeholder with parameters
    @Placeholder(
            value = "get_user_balance_[currency]", // parameters between square brackets are optional,
            // if you need required parameters, use <parameter_name>
            description = "Formats a number to two decimal places."
    )
    // @Param annotation is optional, if you don't use it, the method parameter name must match the placeholder parameter name
    public String getUserBalancePlaceholder(Player player, @Param("currency") String currency) {
        if (currency == null || currency.isEmpty()) {
            currency = "BRL";
        }
        // ...
    }
}
```

## Components

Spigot Boot provides a set of component annotations that you can use in your plugin. These components are managed by the
IOC
container and can be injected into other components.

- `@Component`: Marks a class as a component that can be injected into other components, this is the base annotation
  for all components.
- `@Service`: Marks a class as a service component, which is a specialized type of component that can be injected
  into other components.
- `@Configuration`: Marks a class as a configuration component, which is used to inject beans into the context.
- `@RegisterPlaceholder`: Marks a class as a placeholder component, which is used to register placeholders in the
  Placeholder module.
- `@RegisterMethodHandler` : Marks a class as a method handler component, which is used to register method interceptors
  for
  context-managed classes

### Creating a Component class:

```java
import tech.guilhermekaua.spigotboot.annotations.Component;

@Component
public class MyComponent {
    // This class can be injected into other components
}
```

## Spigot Data Orm Lite

Spigot Boot provides a data module that integrates with OrmLite, allowing you to manage your plugin's data easily.
You can create a entity class as you would do with OrmLite:

```java

@DatabaseTable(tableName = "users")
public class User {
    @DatabaseField(generatedId = true, columnName = "id", canBeNull = false, dataType = DataType.UUID)
    private final UUID uuid;
    @DatabaseField(columnName = "name", canBeNull = false)
    private String name;
    // Other fields...
}
```

And you can create repositories to manage your entities:

```java
import me.approximations.spigotboot.data.ormLite.repository.OrmLiteRepository;

public interface UserRepository extends OrmLiteRepository<User, UUID> {
    default User findByUuid(UUID uuid) throws SQLException {
        return queryBuilder()
                .where()
                .eq("uuid", uuid.toString())
                .queryForFirst();
    }
}
```

Transactions are handled automatically by the data module, so you don't need to worry about managing them manually.

> [!NOTE]  
> I couldn't find a less ugly way to create a repository, if you have any suggestions, please open an issue, i'll be
> happy
> to analyse that.

## Method Interceptors

Spigot Boot provides a method interceptor system that allows you to intercept method calls and execute custom logic
before or after the method call. You can use the `@RegisterMethodHandler` and `@MethodHandler` annotations to register
method interceptors:

```java

@RequiredArgsConstructor
@RegisterMethodHandler
public class OrmLiteRepositoryMethodHandler {
    @MethodHandler(
            targetClass = SomeClass.class,
            classAnnotatedWith = @SomeAnnotation, // Optional, if you want to intercept only methods of a class annotated with a specific annotation
            methodAnnotatedWith = @SomeOtherAnnotation // Optional, if you want to intercept only methods annotated with a specific annotation
    )
    public Object handle(MethodHandlerContext context) throws Throwable {
        if (context.thisMethod() == null) { // thisMethod can be null if you are intercepting methods of a interface class, which don't have a implementation for the method in question
            return null;
        }

        try {
            return context.proceed().invoke(context.self(), context.args());
        } catch (Exception e) {
            // Handle exceptions here
        }
    }
}
```

Method interceptors can be really useful for creating modules for Spigot Boot, for example, the OrmLite data module has
one method interceptor to
internally handle calls to any method of a class/interface that extends `OrmLiteRepository`, performing the necessary
logic to real methods under
the hood, such as transaction management and query building.

## Credits:

- NBTEditor: https://github.com/BananaPuncher714/NBTEditor

TODO:

- [ ] Support for more spigot-related annotations, such as `@EventHandler`, `@Command`, etc.
- [ ] (Plugin Messaging) Add support for custom subchannels and custom messages responses.