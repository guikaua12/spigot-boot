package me.approximations.apxPlugin.testPlugin.services;

import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import me.approximations.apxPlugin.di.annotations.Inject;
import me.approximations.apxPlugin.persistence.jpa.service.annotations.Service;
import me.approximations.apxPlugin.testPlugin.People;
import me.approximations.apxPlugin.testPlugin.repositories.UserRepository;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@RequiredArgsConstructor
@NoArgsConstructor(force=true)
@Service
public class UserService {
    private final String name = "UserService";

    @Inject
    private final UserRepository userRepository;

    @Service
    public CompletableFuture<Optional<People>> getPeople(String uuid) {
        if (true) {
            throw new CompletionException(new InvitedBySomeoneYouInvitedException("This is a test exception"));
        }
        return CompletableFuture.completedFuture(Optional.ofNullable(userRepository.findById(uuid)));
    }

    @Service
    public CompletableFuture<People> savePeople(People people) {
        return CompletableFuture.completedFuture(userRepository.save(people));
    }
}
