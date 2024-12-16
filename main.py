import pickle
import torch
from torch import nn
from torch.nn.utils.rnn import pack_padded_sequence, pad_packed_sequence
import os, sys
import subprocess

complexity_classes = ['logn', 'cubic', 'linear', 'nlogn', 'quadratic', 'np', 'constant']

padding_idx = 2
class LSTMModel(nn.Module):
    def __init__(self, 
                 vocab_size, 
                 embedding_dim, 
                 hidden_size, 
                 output_size, 
                 num_layers, 
                 padding_idx=padding_idx, 
                 dropout_rate=0.5,  # Added dropout rate
                 batch_first=True):
        super(LSTMModel, self).__init__()

        self.class_labels = complexity_classes
        self.padding_idx = padding_idx
        self.embedding = nn.Embedding(vocab_size, embedding_dim, padding_idx=padding_idx)
        self.lstm = nn.LSTM(embedding_dim, hidden_size, num_layers, 
                            batch_first=batch_first, dropout=dropout_rate if num_layers > 1 else 0.0)
        self.fc = nn.Linear(hidden_size, output_size)
        self.dropout = nn.Dropout(dropout_rate)  # Add dropout layer

    def forward(self, x, mask):
        embedded = self.embedding(x)
        embedded = self.dropout(embedded)  # Apply dropout after embedding
        
        lengths = mask.sum(dim=1).cpu()
        packed_input = pack_padded_sequence(embedded, lengths, batch_first=True, enforce_sorted=False)
        packed_output, (hn, cn) = self.lstm(packed_input)
        output, _ = pad_packed_sequence(packed_output, batch_first=True)
        
        final_output = hn[-1]
        final_output = self.dropout(final_output)  # Apply dropout before the fully connected layer
        output = self.fc(final_output)
        return output

    def predict(self, x):
        self.eval()
        with torch.no_grad():
            mask = (x != self.padding_idx).float()
            output = self(x, mask)
        probabilities = torch.softmax(output, dim=1)
        _, predicted_class = torch.max(probabilities, dim=1)
        return self.class_labels[predicted_class]

def main():
    
    sourceCode = sys.argv[1]
    with open("simplified_tokenizer", 'rb') as f:
        tokenizer = pickle.load(f)
    
    
    state_dict = torch.load("final_model.checkpoint")
    model = LSTMModel()
    model._load_from_state_dict(state_dict)
    
    
    result = subprocess.run(['java', '-jar', 'code2ast/target/code2ast-1.0-SNAPSHOT.jar', 'print_cu', sourceCode])
    simplified_src = result.stdout.decode()
    tokens = tokenizer.tokenize(simplified_src)
    x = torch.tensor([tokens], dtype=torch.long)
    
    
    output = model.predict(x)
    print(output)


if __name__ == "__main__":
    main()