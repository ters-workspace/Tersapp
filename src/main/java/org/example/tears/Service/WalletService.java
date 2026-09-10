package org.example.tears.Service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.example.tears.Api.ApiException;
import org.example.tears.DTO.ConfirmMobilePaymentRequest;
import org.example.tears.DTO.MobilePaymentResponse;
import org.example.tears.DTO.WalletResponseDto;
import org.example.tears.DTO.WalletTopupRequest;
import org.example.tears.Enums.PaymentMethod;
import org.example.tears.Enums.PaymentStatus;
import org.example.tears.Enums.TransactionType;
import org.example.tears.Model.PaymentIntent;
import org.example.tears.Model.User;
import org.example.tears.Model.Wallet;
import org.example.tears.Model.WalletTransaction;
import org.example.tears.Repository.PaymentIntentRepository;
import org.example.tears.Repository.WalletRepository;
import org.example.tears.Repository.WalletTransactionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import lombok.extern.slf4j.Slf4j;


import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final AuthService authService;
    private final PaymentIntentRepository paymentIntentRepository;


    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${MOYASAR_SECRET_KEY}")
    private String secretKey;



    public Wallet getOrCreate(User user) {
        return walletRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    Wallet w = new Wallet();
                    w.setUser(user);
                    w.setBalance(0);
                    w.setCreatedAt(LocalDateTime.now());
                    return walletRepository.save(w);
                });
    }

    public Wallet getMyWallet(User user) {
        return getOrCreate(user);
    }


    public List<WalletTransaction> getMyTransactions(User user) {
        Wallet wallet = getOrCreate(user);
        return walletTransactionRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId());
    }

    @Transactional
    public void payFromWallet(User user, Integer amountHalalah, String ref) {

        if (amountHalalah == null || amountHalalah <= 0) {
            throw new ApiException("المبلغ غير صحيح");
        }

        Wallet wallet = walletRepository.findByUserIdForUpdate(user.getId())
                .orElseThrow(() ->
                        new ApiException("المحفظة غير موجودة"));

        if (wallet.getBalance() == null) {
            throw new ApiException("رصيد المحفظة غير موجود");
        }

        if (wallet.getBalance() < amountHalalah) {
            throw new ApiException("رصيد المحفظة غير كافي");
        }

        wallet.setBalance(wallet.getBalance() - amountHalalah);
        walletRepository.save(wallet);

        WalletTransaction tx = new WalletTransaction();
        tx.setWallet(wallet);
        tx.setAmount(amountHalalah);
        tx.setType(TransactionType.PAYMENT);
        tx.setPaymentMethod(PaymentMethod.WALLET);
        tx.setReferenceNumber(ref);
        tx.setDescription("Payment for request");
        tx.setCreatedAt(LocalDateTime.now());

        walletTransactionRepository.save(tx);
    }

    public void deposit(User user, Integer amount) {
        Wallet wallet = getOrCreate(user);

        if (amount == null || amount <= 0) {
            throw new ApiException("المبلغ غير صحيح");
        }

        wallet.setBalance(wallet.getBalance() + amount);
        walletRepository.save(wallet);

        WalletTransaction tx = new WalletTransaction();
        tx.setWallet(wallet);
        tx.setAmount(amount);
        tx.setType(TransactionType.DEPOSIT);
        tx.setPaymentMethod(PaymentMethod.WALLET);
        tx.setReferenceNumber(UUID.randomUUID().toString());
        tx.setDescription("Wallet deposit");
        tx.setCreatedAt(LocalDateTime.now());

        walletTransactionRepository.save(tx);
    }

    //لشحن البطاقه من التطبيق
    @Transactional
    public MobilePaymentResponse prepareWalletTopUp(
            HttpServletRequest request,
            WalletTopupRequest dto
    ) {

        User user = authService.getAuthenticatedUser(request);

        if (dto.getAmount() == null || dto.getAmount() <= 0) {
            throw new ApiException("Invalid amount");
        }

        PaymentIntent intent = new PaymentIntent();

        intent.setCustomer(user.getCustomer());

        intent.setInitialPaymentAmountHalalah(dto.getAmount());

        intent.setInitialPaymentAmount(dto.getAmount() / 100.0);

        intent.setPaymentMethod(PaymentMethod.WALLET);

        intent.setPaymentStatus(PaymentStatus.INITIATED);

        intent.setCreatedAt(LocalDateTime.now());

        intent.setExpiresAt(LocalDateTime.now().plusMinutes(5));

        String givenId = UUID.randomUUID().toString();

        intent.setGivenId(givenId);

        paymentIntentRepository.save(intent);

        log.info(
                "Wallet Topup Prepared {}, givenId={}",
                intent.getId(),
                givenId
        );

        return new MobilePaymentResponse(

                intent.getId(),

                intent.getId(),

                givenId,

                dto.getAmount(),

                "SAR",

                "Wallet Topup",

                "PREPARED"

        );
    }

    //للرد على الشحن من التطبيق
    @Transactional
    public WalletResponseDto confirmWalletTopup(
            ConfirmMobilePaymentRequest dto,
            HttpServletRequest request
    ) {

        User user = authService.getAuthenticatedUser(request);

        PaymentIntent intent =
                paymentIntentRepository.findById(dto.getPaymentAttemptId())
                        .orElseThrow(() ->
                                new ApiException("Payment attempt not found"));

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(secretKey, "");

        ResponseEntity<Map> response =
                restTemplate.exchange(
                        "https://api.moyasar.com/v1/payments/" + dto.getPaymentId(),
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        Map.class
                );

        Map<String, Object> payment = response.getBody();

        if (payment == null) {
            throw new ApiException("Payment not found");
        }

        if (!"paid".equalsIgnoreCase(payment.get("status").toString())) {
            throw new ApiException("Payment not completed");
        }

        Integer amount =
                ((Number) payment.get("amount")).intValue();

        if (!amount.equals(intent.getInitialPaymentAmountHalalah())) {
            throw new ApiException("Amount mismatch");
        }

        if (!"SAR".equals(payment.get("currency").toString())) {
            throw new ApiException("Currency mismatch");
        }

        Object metaObj = payment.get("metadata");

        if (!(metaObj instanceof Map<?, ?> metadata)) {
            throw new ApiException("Metadata missing");
        }

        String givenId = metadata.get("givenId").toString();

        if (!givenId.equals(intent.getGivenId())) {
            throw new ApiException("GivenId mismatch");
        }

        if (intent.getPaymentStatus() != PaymentStatus.PAID) {

            deposit(user, amount);

            intent.setPaymentStatus(PaymentStatus.PAID);
            intent.setPaymentId(dto.getPaymentId());
            intent.setPaidAt(LocalDateTime.now());

            paymentIntentRepository.save(intent);
        }

        Wallet wallet = getMyWallet(user);

        return new WalletResponseDto(
                wallet.getId(),
                wallet.getBalance(),
                wallet.getBalance() / 100.0
        );
    }

    @Transactional
    public void refundToWallet(
            User user,
            Integer amountHalalah,
            String referenceNumber,
            String description
    ) {

        if (amountHalalah == null || amountHalalah <= 0) {
            throw new ApiException("مبلغ الاسترداد غير صحيح");
        }

        Wallet wallet =
                walletRepository.findByUserIdForUpdate(user.getId())
                        .orElseGet(() -> {

                            Wallet newWallet = new Wallet();

                            newWallet.setUser(user);
                            newWallet.setBalance(0);
                            newWallet.setCreatedAt(LocalDateTime.now());

                            return walletRepository.save(newWallet);
                        });

        wallet.setBalance(
                wallet.getBalance() + amountHalalah
        );

        walletRepository.save(wallet);

        WalletTransaction transaction =
                new WalletTransaction();

        transaction.setWallet(wallet);
        transaction.setAmount(amountHalalah);
        transaction.setType(TransactionType.REFUND);
        transaction.setPaymentMethod(PaymentMethod.WALLET);
        transaction.setReferenceNumber(referenceNumber);
        transaction.setDescription(description);
        transaction.setCreatedAt(LocalDateTime.now());

        walletTransactionRepository.save(transaction);
    }

}