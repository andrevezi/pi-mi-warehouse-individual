package br.com.group9.pimlwarehouse.service;

import br.com.group9.pimlwarehouse.dto.ProductDTO;
import br.com.group9.pimlwarehouse.entity.BatchStock;
import br.com.group9.pimlwarehouse.entity.InboundOrder;
import br.com.group9.pimlwarehouse.entity.Section;
import br.com.group9.pimlwarehouse.entity.SectionProduct;
import br.com.group9.pimlwarehouse.exception.ProductDoesNotMatchSectionException;
import br.com.group9.pimlwarehouse.exception.SectionNotFoundException;
import br.com.group9.pimlwarehouse.exception.SectionProductNotFoundException;
import br.com.group9.pimlwarehouse.exception.InboundOrderValidationException;
import br.com.group9.pimlwarehouse.repository.SectionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SectionService {
    private SectionRepository sectionRepository;
    private SectionProductService sectionProductService;
    private ProductAPIService productAPIService;

    public SectionService(SectionRepository sectionRepository, SectionProductService sectionProductService, ProductAPIService productAPIService) {
        this.sectionRepository = sectionRepository;
        this.sectionProductService = sectionProductService;
        this.productAPIService = productAPIService;
    }

    /**
     * Search a section by Id.
     * @param id receives a sectionId to perform a search.
     * @return the result of search, if not find, this will return a "SECTION_NOT_FOUND".
     */
    public Section findById(Long id) {
        return this.sectionRepository.findById(id)
                .orElseThrow(() -> new SectionNotFoundException("SECTION_NOT_FOUND"));
    }

    /**
     * Verify the batchStocks size.
     * @param batchStocks receives a List<BatchStock> with the size.
     * @return the size of batch stock informed.
     */
    private Double getTotalBatchSize(List<BatchStock> batchStocks){
        return batchStocks.stream().map(
                e -> e.getProductSize() * e.getCurrentQuantity()
        ).mapToDouble(Double::doubleValue).sum();
    }

    /**
     * Verify the section size.
     * @param section receives a Section with the size.
     * @return the size of Section informed.
     */
    public Double getAvailableSpace(Section section){
        List<InboundOrder> inboundOrders = section.getInboundOrders();
        Double occupiedSpace = inboundOrders.stream().map(
                order -> getTotalBatchSize(order.getBatchStocks())
        ).mapToDouble(Double::doubleValue).sum();

        return section.getSize()-occupiedSpace;

    }

    /**
     * Verify if Section exists and if haves enough space for store InboundOrder.
     * @param sectionId receives a Long sectionId to indicate where it batchstock go.
     * @param warehouseId receives a Long warehouseId to indicate where it batchstock go.
     * @param batchStocks receives a List<BatchStock> to store inside section.
     */
    public void validateBatchStocksBySection(Long sectionId, Long warehouseId, List<BatchStock> batchStocks) {
        Section section = findById(sectionId);

        if(section.getWarehouse().getId() != warehouseId)
            throw new InboundOrderValidationException("SECTION_WAREHOUSE_DOES_NOT_MATCH");

        boolean productMatchesSection = batchStocks.stream()
                .map(b -> b.getProductId())
                .anyMatch(prodId ->
                        section.getSectionProducts().stream()
                                .filter(sectionProduct -> sectionProduct.getProductId() == prodId)
                                .findFirst().orElse(null) == null
                );
        if(productMatchesSection)
            throw new InboundOrderValidationException("PRODUCT_SECTION_DOES_NOT_MATCH");

        Double availableSpace = getAvailableSpace(section);
        Double requiredSpace = getTotalBatchSize(batchStocks);
        if (requiredSpace > availableSpace){
            throw new InboundOrderValidationException("SECTION_SPACE_NOT_ENOUGH");
        }
    }

    /**
     * Validates that the temperature is in accordance with the section.
     * @param productDTO receives a productDTO to validate the temperature.
     * @param section receives a section to validate the temperature with product.
     */

    public void validateProductToSectionAssociation(ProductDTO productDTO, Section section) {
        if(productDTO.getMinimumTemperature() < section.getMinimalTemperature())
            throw new ProductDoesNotMatchSectionException("EXCEEDING_SECTION_MINIMUM_TEMPERATURE");
        if(productDTO.getMinimumTemperature() > section.getMaximalTemperature())
            throw new ProductDoesNotMatchSectionException("INFERIOR_SECTION_MAXIMUM_TEMPERATURE");
    }

    /**
     * Associate the product with a section by both ids.
     * @param sectionId receives a Long sectionId where will he get the product.
     * @param productId receives a Long productId where it will be associated with section.
     * @return an association between product and section, if validation passes.
     */

    public Section associateProductToSectionByIds(Long sectionId, Long productId) {
        Section foundSection = this.sectionRepository.findById(sectionId)
                .orElseThrow(() -> new SectionNotFoundException("SECTION_NOT_FOUND"));

        ProductDTO foundProductDTO = this.productAPIService.fetchProductById(productId);

        validateProductToSectionAssociation(foundProductDTO, foundSection);

        SectionProduct newSectionProduct = SectionProduct.builder()
                .section(foundSection)
                .productId(productId)
                .build();
        if(this.sectionProductService.exists(newSectionProduct))
            throw new SectionProductNotFoundException("SECTION_PRODUCT_ALREADY_ASSOCIATED");
        foundSection.addSectionProduct(newSectionProduct);

        return this.sectionRepository.save(foundSection);

    }
}
