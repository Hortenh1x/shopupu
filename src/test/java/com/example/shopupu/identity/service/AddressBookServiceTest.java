package com.example.shopupu.identity.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.shopupu.common.exception.ResourceNotFoundException;
import com.example.shopupu.identity.dto.AddressRequest;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.entity.UserAddress;
import com.example.shopupu.identity.repository.UserAddressRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AddressBookServiceTest {

    @Mock
    private UserAddressRepository addressRepository;

    @InjectMocks
    private AddressBookService addressBookService;

    private final User user = User.builder().id(1L).email("user@example.com").build();

    @Test
    void firstAddressBecomesDefaultAutomatically() {
        when(addressRepository.findByUserOrderByDefaultAddressDescCreatedAtAsc(user)).thenReturn(List.of());
        when(addressRepository.save(any(UserAddress.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserAddress saved = addressBookService.addAddress(user, request(false));

        assertTrue(saved.isDefaultAddress());
        verify(addressRepository).clearDefault(user);
    }

    @Test
    void secondAddressIsNotDefaultUnlessRequested() {
        when(addressRepository.findByUserOrderByDefaultAddressDescCreatedAtAsc(user))
                .thenReturn(List.of(new UserAddress()));
        when(addressRepository.save(any(UserAddress.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserAddress saved = addressBookService.addAddress(user, request(false));

        assertFalse(saved.isDefaultAddress());
        verify(addressRepository, never()).clearDefault(user);
    }

    @Test
    void setDefaultSwitchesTheFlag() {
        UserAddress address = UserAddress.builder().id(5L).user(user).defaultAddress(false).build();
        when(addressRepository.findByIdAndUser(5L, user)).thenReturn(Optional.of(address));
        when(addressRepository.save(any(UserAddress.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserAddress saved = addressBookService.setDefault(user, 5L);

        assertTrue(saved.isDefaultAddress());
        verify(addressRepository).clearDefault(user);
    }

    @Test
    void foreignAddressIsInvisible() {
        when(addressRepository.findByIdAndUser(42L, user)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> addressBookService.setDefault(user, 42L));
        assertThrows(ResourceNotFoundException.class, () -> addressBookService.deleteAddress(user, 42L));
    }

    private AddressRequest request(boolean isDefault) {
        return new AddressRequest("John Doe", "Main st 1", null, "Kyiv", null, "01001", "Ukraine", isDefault);
    }
}
