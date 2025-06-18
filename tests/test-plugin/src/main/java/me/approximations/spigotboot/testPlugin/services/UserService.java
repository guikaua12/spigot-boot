/*
 * The MIT License
 * Copyright © 2025 Guilherme Kauã da Silva
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
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
