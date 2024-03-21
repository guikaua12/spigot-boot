# ApxPlugin

ApxPlugin is basically a library inspirated on Spring Framework but for spigot plugins, it encapsulates a Dependency
Injection, transactional services, repositories, using JPA with Hibernate, and more.

## BungeeCord Plugin messaging channel

ApxPlugin has a convenient API to send messages between BungeeCord and Spigot servers:

```java
public class Main extends ApxPlugin {
    @Override
    protected void onPluginEnable() {
        BungeeChannel bungeeChannel = new BungeeChannel(this);

        // Send a message to BungeeCord to get the server name of a player by its name
        bungeeChannel.sendMessage(null, new GetPlayerServerAction(player.getName())).thenAccept(serverName -> {
            player.sendMessage("You are on server: " + serverName);
        }).exceptionally(throwable -> {
            player.sendMessage("An error occurred while trying to fetch your server.");
            throwable.printStackTrace();
            return null;
        });

        // you saw? no need to rack your brains filling your code with boilerplate
        // there are two types of Actions, normal ones, and responsible ones which return some value 
        // (such in the example above, we are using `GetPlayerServerAction` which extends `ResponseableMessageAction`)
        // you can find all message actions here: https://github.com/guikaua12/ApxPlugin/tree/master/core/src/main/java/me/approximations/apxPlugin/messaging/bungee/actions
    }
}
```

## Credits:
- NBTEditor: https://github.com/BananaPuncher714/NBTEditor


TODO:

- [ ] Add support for custom subchannels and custom messages responses.
