package greencity.service;

import greencity.ModelUtils;
import greencity.dto.OrderResponseDto;
import greencity.dto.PersonalDataDto;
import greencity.dto.UserPointsAndAllBagsDto;
import greencity.entity.enums.CertificateStatus;
import greencity.entity.order.Bag;
import greencity.entity.order.Certificate;
import greencity.entity.order.Order;
import greencity.entity.user.User;
import greencity.entity.user.ubs.UBSuser;
import greencity.exceptions.CertificateNotFoundException;
import greencity.repository.BagRepository;
import greencity.repository.CertificateRepository;
import greencity.repository.UBSuserRepository;
import greencity.repository.UserRepository;
import greencity.service.ubs.UBSClientServiceImpl;
import java.util.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.anyLong;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

@ExtendWith(MockitoExtension.class)
class UBSClientServiceImplTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private BagRepository bagRepository;
    @Mock
    private UBSuserRepository ubsUserRepository;
    @Mock
    private ModelMapper modelMapper;
    @Mock
    private CertificateRepository certificateRepository;
    @InjectMocks
    UBSClientServiceImpl ubsService;

    @Test
    void getFirstPageData() {
        Long userId = 13L;
        User user = new User();
        user.setCurrentPoints(254);
        List<Bag> bags = Collections.singletonList(new Bag(120, 1, "name", 250));

        when(userRepository.findById(anyLong())).thenReturn(Optional.of(user));
        when(bagRepository.findAll()).thenReturn(bags);

        UserPointsAndAllBagsDto expected = ubsService.getFirstPageData(userId);

        assertTrue(254 == expected.getPoints() && bags == expected.getAllBags());
    }

    @Test
    void getSecondPageData() {
        PersonalDataDto expected = ModelUtils.getOrderResponceDto().getPersonalData();

        when(ubsUserRepository.getAllByUserId(anyLong()))
            .thenReturn(Collections.singletonList(ModelUtils.getUBSuser()));
        when(modelMapper.map(ModelUtils.getUBSuser(), PersonalDataDto.class)).thenReturn(expected);

        assertEquals(expected, ubsService.getSecondPageData(13L).get(0));
    }

    @Test
    void getSecondPageDataForNewBuyer() {
        User user = new User();
        user.setEmail("mail@mail.ua");

        when(ubsUserRepository.getAllByUserId(anyLong())).thenReturn(Collections.emptyList());
        when(userRepository.findById(anyLong())).thenReturn(Optional.of(user));

        assertEquals("mail@mail.ua", ubsService.getSecondPageData(13L).get(0).getEmail());
    }

    @Test
    void checkCertificate() {
        when(certificateRepository.findById("certificate")).thenReturn(Optional.of(Certificate.builder()
            .code("certificate")
            .certificateStatus(CertificateStatus.ACTIVE)
            .build()));

        assertEquals("ACTIVE", ubsService.checkCertificate("certificate").getCertificateStatus());
    }

    @Test
    void checkCertificateWithNoAvailable() {
        Assertions.assertThrows(CertificateNotFoundException.class, () -> {
            ubsService.checkCertificate("randomstring").getCertificateStatus();
        });
    }

    @Test
    void processOrder() {
        OrderResponseDto dto = ModelUtils.getOrderResponceDto();
        User user = new User();
        user.setOrders(new ArrayList<>());
        user.setChangeOfPoints(new HashMap<>());
        user.setCurrentPoints(300);
        user.setUbsUsers(new HashSet<>());
        UBSuser ubSuser = new UBSuser();
        ubSuser.setId(10L);
        Order order = new Order();
        order.setCertificates(new HashSet<>());

        when(userRepository.findById(13L)).thenReturn(Optional.of(user));
        when(modelMapper.map(dto.getPersonalData(), UBSuser.class)).thenReturn(ubSuser);
        when(ubsUserRepository.findById(10L)).thenReturn(Optional.of(new UBSuser()));
        when(modelMapper.map(dto, Order.class)).thenReturn(order);
        when(bagRepository.findById(3)).thenReturn(Optional.of(new Bag()));

        ubsService.saveFullOrderToDB(dto, 13L);

        verify(userRepository, times(1)).findById(13L);
        verify(modelMapper, times(1)).map(dto, Order.class);
        verify(userRepository, times(1)).save(any(User.class));
    }
}