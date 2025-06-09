package com.yourcompany.agritrade.ordering.service;

import com.yourcompany.agritrade.ordering.dto.request.SupplyOrderPlacementRequest;
import com.yourcompany.agritrade.ordering.dto.response.OrderResponse;
import com.yourcompany.agritrade.ordering.dto.response.SupplyOrderRequestResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;

public interface SupplyOrderRequestService {
    SupplyOrderRequestResponse createSupplyOrderRequest(Authentication authentication, SupplyOrderPlacementRequest request);
    Page<SupplyOrderRequestResponse> getMySentRequests(Authentication authentication, Pageable pageable);
    Page<SupplyOrderRequestResponse> getMyReceivedRequests(Authentication authentication, Pageable pageable);
    SupplyOrderRequestResponse getRequestDetails(Authentication authentication, Long requestId);
    SupplyOrderRequestResponse  acceptSupplyOrderRequest(Authentication authentication, Long requestId /*, Optional: AgreedPrice, etc. */);
    SupplyOrderRequestResponse rejectSupplyOrderRequest(Authentication authentication, Long requestId, String reason);
     void cancelSupplyOrderRequestByBuyer(Authentication authentication, Long requestId); // Tùy chọn
}
