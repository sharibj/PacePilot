package com.pacepilot.app.item;

import com.pacepilot.app.common.NotFoundException;
import com.pacepilot.app.item.dto.ItemRequest;
import com.pacepilot.app.item.dto.ItemResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ItemService {

  private final ItemRepository repository;

  public ItemService(ItemRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public List<ItemResponse> findAll() {
    return repository.findAll().stream().map(ItemResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public ItemResponse findById(Long id) {
    return repository.findById(id).map(ItemResponse::from).orElseThrow(() -> notFound(id));
  }

  @Transactional
  public ItemResponse create(ItemRequest request) {
    return ItemResponse.from(repository.save(new Item(request.name())));
  }

  @Transactional
  public ItemResponse update(Long id, ItemRequest request) {
    Item item = repository.findById(id).orElseThrow(() -> notFound(id));
    item.setName(request.name());
    return ItemResponse.from(repository.save(item));
  }

  @Transactional
  public void delete(Long id) {
    if (!repository.existsById(id)) {
      throw notFound(id);
    }
    repository.deleteById(id);
  }

  private NotFoundException notFound(Long id) {
    return new NotFoundException("Item not found: " + id);
  }
}
