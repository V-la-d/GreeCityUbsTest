package greencity.service.ubs;

import greencity.constant.ErrorMessage;
import greencity.dto.CertificateDto;
import greencity.dto.OrderResponseDto;
import greencity.dto.PersonalDataDto;
import greencity.dto.UserPointsAndAllBagsDto;
import greencity.entity.enums.CertificateStatus;
import greencity.entity.enums.OrderStatus;
import greencity.entity.order.Bag;
import greencity.entity.order.Certificate;
import greencity.entity.order.Order;
import greencity.entity.user.User;
import greencity.entity.user.ubs.UBSuser;
import greencity.exceptions.BagNotFoundException;
import greencity.exceptions.CertificateExpiredException;
import greencity.exceptions.CertificateIsUsedException;
import greencity.exceptions.CertificateNotFoundException;
import greencity.repository.BagRepository;
import greencity.repository.CertificateRepository;
import greencity.repository.UBSuserRepository;
import greencity.repository.UserRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

/**
 * Implementation of {@link UBSClientService}.
 */
@Service
@AllArgsConstructor
public class UBSClientServiceImpl implements UBSClientService {
    private final UserRepository userRepository;
    private final BagRepository bagRepository;
    private final UBSuserRepository ubsUserRepository;
    private final ModelMapper modelMapper;
    private final CertificateRepository certificateRepository;

    /**
     * {@inheritDoc}
     */
    @Override
    public UserPointsAndAllBagsDto getFirstPageData(Long userId) {
        return new UserPointsAndAllBagsDto((List<Bag>) bagRepository.findAll(),
            userRepository.findById(userId).get().getCurrentPoints());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public List<PersonalDataDto> getSecondPageData(Long userId) {
        List<UBSuser> allByUserId = ubsUserRepository.getAllByUserId(userId);
        if (allByUserId.isEmpty()) {
            return List.of(PersonalDataDto.builder().email(userRepository.findById(userId).get().getEmail()).build());
        }

        return allByUserId.stream().map(u -> modelMapper.map(u, PersonalDataDto.class)).collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CertificateDto checkCertificate(String code) {
        Certificate certificate = certificateRepository.findById(code)
            .orElseThrow(() -> new CertificateNotFoundException(ErrorMessage.CERTIFICATE_NOT_FOUND_BY_CODE + code));

        return new CertificateDto(certificate.getCertificateStatus().toString(), certificate.getPoints(),
            certificate.getDate());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void saveFullOrderToDB(OrderResponseDto dto, Long userId) {
        User currentUser = userRepository.findById(userId).get();
        Order order = modelMapper.map(dto, Order.class);

        Map<Integer, Integer> map = new HashMap<>();
        dto.getBags().forEach(bag -> {
            bagRepository.findById(bag.getId())
                .orElseThrow(() -> new BagNotFoundException(ErrorMessage.BAG_NOT_FOUND + bag.getId()));
            map.put(bag.getId(), bag.getAmount());
        });

        Set<Certificate> orderCertificates = new HashSet<>();
        dto.getCerfiticates().forEach(c -> {
            Certificate temp = certificateRepository.findById(c)
                .orElseThrow(() -> new CertificateNotFoundException(ErrorMessage.CERTIFICATE_NOT_FOUND_BY_CODE + c));
            validateCertificate(temp);
            temp.setCertificateStatus(CertificateStatus.USED);
            temp.setOrder(order);
            orderCertificates.add(temp);
        });

        UBSuser ubsUser = modelMapper.map(dto.getPersonalData(), UBSuser.class);
        ubsUser.setUser(currentUser);
        if (ubsUser.getId() == null || !ubsUser.equals(ubsUserRepository.findById(ubsUser.getId()).get())) {
            ubsUser.setId(null);
            ubsUserRepository.save(ubsUser);
            currentUser.getUbsUsers().add(ubsUser);
        } else {
            ubsUser = ubsUserRepository.findById(ubsUser.getId()).get();
        }

        order.setOrderStatus(OrderStatus.NEW);
        order.setCertificates(orderCertificates);
        order.setAmountOfBagsOrdered(map);
        order.setUbsUser(ubsUser);
        order.setUser(currentUser);

        currentUser.getOrders().add(order);
        currentUser.setCurrentPoints(currentUser.getCurrentPoints() - dto.getPointsToUse());
        currentUser.getChangeOfPoints().put(order.getOrderDate(), -dto.getPointsToUse());

        userRepository.save(currentUser);
    }

    private void validateCertificate(Certificate certificate) {
        if (certificate.getCertificateStatus() == CertificateStatus.USED) {
            throw new CertificateIsUsedException(ErrorMessage.CERTIFICATE_IS_USED + certificate.getCode());
        } else {
            LocalDate future = certificate.getDate().plusYears(1);
            if (future.isBefore(LocalDate.now())) {
                throw new CertificateExpiredException(ErrorMessage.CERTIFICATE_EXPIRED + certificate.getCode());
            }
        }
    }
}