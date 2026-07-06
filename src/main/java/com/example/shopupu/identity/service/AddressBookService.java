package com.example.shopupu.identity.service;

import com.example.shopupu.common.exception.ResourceNotFoundException;
import com.example.shopupu.identity.dto.AddressRequest;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.entity.UserAddress;
import com.example.shopupu.identity.repository.UserAddressRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AddressBookService {

    private final UserAddressRepository addressRepository;

    @Transactional(readOnly = true)
    public List<UserAddress> getAddresses(User user) {
        return addressRepository.findByUserOrderByDefaultAddressDescCreatedAtAsc(user);
    }

    @Transactional
    public UserAddress addAddress(User user, AddressRequest request) {
        boolean firstAddress = addressRepository.findByUserOrderByDefaultAddressDescCreatedAtAsc(user).isEmpty();
        boolean makeDefault = Boolean.TRUE.equals(request.defaultAddress()) || firstAddress;
        if (makeDefault) {
            addressRepository.clearDefault(user);
        }
        UserAddress address = UserAddress.builder()
                .user(user)
                .defaultAddress(makeDefault)
                .build();
        apply(address, request);
        return addressRepository.save(address);
    }

    @Transactional
    public UserAddress updateAddress(User user, Long addressId, AddressRequest request) {
        UserAddress address = requireOwn(user, addressId);
        if (Boolean.TRUE.equals(request.defaultAddress()) && !address.isDefaultAddress()) {
            addressRepository.clearDefault(user);
            address.setDefaultAddress(true);
        }
        apply(address, request);
        return addressRepository.save(address);
    }

    @Transactional
    public UserAddress setDefault(User user, Long addressId) {
        UserAddress address = requireOwn(user, addressId);
        addressRepository.clearDefault(user);
        address.setDefaultAddress(true);
        return addressRepository.save(address);
    }

    @Transactional
    public void deleteAddress(User user, Long addressId) {
        UserAddress address = requireOwn(user, addressId);
        addressRepository.delete(address);
    }

    private void apply(UserAddress address, AddressRequest request) {
        address.setFullName(request.fullName());
        address.setLine1(request.line1());
        address.setLine2(request.line2());
        address.setCity(request.city());
        address.setState(request.state());
        address.setPostalCode(request.postalCode());
        address.setCountry(request.country());
    }

    private UserAddress requireOwn(User user, Long addressId) {
        // scoped lookup by owner: no IDOR via address ids (AUTHZ-04)
        return addressRepository.findByIdAndUser(addressId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));
    }
}
