package com.incentives.piggyback.partner.serviceimpl;

import java.util.List;
import java.util.Calendar;
import java.util.Optional;

import com.google.gson.Gson;
import com.incentives.piggyback.partner.entity.*;
import com.incentives.piggyback.partner.publisher.KafkaMessageProducer;
import com.incentives.piggyback.partner.util.CommonUtility;
import com.incentives.piggyback.partner.util.constants.Constant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.stereotype.Service;

import com.incentives.piggyback.partner.adapter.ObjectAdapter;
import com.incentives.piggyback.partner.dto.PartnerEntity;
import com.incentives.piggyback.partner.exception.ExceptionResponseCode;
import com.incentives.piggyback.partner.exception.PiggyException;
import com.incentives.piggyback.partner.repository.PartnerRepository;
import com.incentives.piggyback.partner.service.PartnerService;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;
@Slf4j
@Service
public class PartnerServiceImpl implements PartnerService {

	@Autowired
	private PartnerRepository partnerRepository;

	@Autowired
	Environment env;

	@Autowired
	private RestTemplate restTemplate;

	private final KafkaMessageProducer kafkaMessageProducer;

	Gson gson = new Gson();

	public PartnerServiceImpl(KafkaMessageProducer kafkaMessageProducer) {
		this.kafkaMessageProducer = kafkaMessageProducer;
	}

	@Override
	public PartnerEntity createPartner(Partner partner,HttpServletRequest request){
		if(isAuthorized(request.getHeader("Authorization"))){
		PartnerEntity partnerEntity = partnerRepository.save(ObjectAdapter.getPartnerEntity(partner));
		publishPartner(partnerEntity, Constant.PARTNER_CREATED_EVENT);
		return partnerEntity;
	    }else {
			throw new PiggyException(ExceptionResponseCode.USER_DATA_NOT_FOUND_IN_RESPONSE);
		}
	}

	@Override
	public PartnerEntity getPartner(String partnerId) {
		Optional<PartnerEntity> partnerOptional = partnerRepository.findById(partnerId);
		if (partnerOptional.isPresent()) {
			return partnerOptional.get();
		} else {
			throw new PiggyException(ExceptionResponseCode.USER_DATA_NOT_FOUND_IN_RESPONSE);
		}
	}

	public List<PartnerEntity> getAllPartner() {
		return partnerRepository.findAll();
	}

	@Override
	public String deletePartner(String partnerId) {
		PartnerEntity partner = getPartner(partnerId);
		partner.setIsActive(0);
		partnerRepository.save(partner);
		return "Partner deactivated successfully!";
	}

	@Override
	public PartnerEntity updatePartner(Partner partner, HttpServletRequest request) {
		if(isAuthorized(request.getHeader("Authorization"))) {
			UserPartnerDto userPartnerDto = new UserPartnerDto();
			PartnerEntity currentPartnerValue = getPartner(partner.getPartnerId());
			PartnerEntity partnerEntity = partnerRepository.save(ObjectAdapter.updatePartnerEntity(currentPartnerValue, partner));
			userPartnerDto.setPartnerId(partner.getPartnerId());
			userPartnerDto.setUserId(Long.valueOf(partner.getUserIds().get(0)));
			publishPartner(userPartnerDto,Constant.USER_PARTNER_MAPPING);
//			updatePartnerIdForUser(Long.valueOf(partnerEntity.getUserIds().get(0)),partnerEntity.getPartnerId());
			publishPartner(partnerEntity, Constant.PARTNER_UPDATED_EVENT);
			return partnerEntity;
		}else {
			throw new PiggyException(ExceptionResponseCode.USER_DATA_NOT_FOUND_IN_RESPONSE);
		}
	}



	private void publishPartner(PartnerEntity partner, String status) {
		kafkaMessageProducer.send(
				CommonUtility.stringifyEventForPublish(
						gson.toJson(partner),
						status,
						Calendar.getInstance().getTime().toString(),
						"",
						Constant.PARTNER_SOURCE_ID
				)
		);
	}

	private void publishPartner(UserPartnerDto userPartnerDto, String status) {
		kafkaMessageProducer.send(
				CommonUtility.stringifyEventForPublish(
						gson.toJson(userPartnerDto),
						status,
						Calendar.getInstance().getTime().toString(),
						"",
						Constant.PARTNER_SOURCE_ID
				)
		);
	}

	private boolean isAuthorized(String accessToken) {
		log.info("Partner Service: User token validation from user service");
		String url = env.getProperty("users.api.userValid");
		HttpHeaders headers = new HttpHeaders();
		headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
		headers.set("Authorization", accessToken);
		HttpEntity<?> entity = new HttpEntity<>(headers);
		ResponseEntity<Integer> response =
				restTemplate.exchange(url, HttpMethod.HEAD,
						entity,Integer.class);
		if (CommonUtility.isNullObject(response.getStatusCode())){
			throw new PiggyException("User not authorized to create partner");
		}else if(response.getStatusCodeValue()==200){
			return true;
		}
		else
			return false;

	}

}