package me.approximations.spigotboot.testPlugin.services;

import lombok.RequiredArgsConstructor;
import me.approximations.spigotboot.core.di.annotations.Service;
import me.approximations.spigotboot.testPlugin.People;
import me.approximations.spigotboot.testPlugin.repositories.UserRepository;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
@Service
public class UserService {
    private final String name = "UserService";
    private final UserRepository userRepository;

    @Service
    public CompletableFuture<Optional<People>> getPeople(UUID uuid) {
//        if (true) {
//            throw new CompletionException(new InvitedBySomeoneYouInvitedException("This is a test exception"));
//        }
        return CompletableFuture.completedFuture(Optional.ofNullable(userRepository.findById(uuid)));
    }

    @Service
    public Optional<People> getPeople2(UUID uuid) {
//        if (true) {
//            throw new CompletionException(new InvitedBySomeoneYouInvitedException("This is a test exception"));
//        }
        return Optional.ofNullable(userRepository.findById(uuid));
    }

    @Service
    public CompletableFuture<People> savePeople(People people) {
        return CompletableFuture.completedFuture(userRepository.save(people));
    }
}
