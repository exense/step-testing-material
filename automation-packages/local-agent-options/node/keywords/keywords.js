exports.Routed_Node_Echo = async (input, output) => {
  output.send({ echo: input.message });
};
